package org.stypox.dicio.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.stypox.dicio.skills.localbrain.GeminiNanoBrain
import org.stypox.dicio.skills.localbrain.LocalBrain
import org.stypox.dicio.skills.image.TextReader
import org.stypox.dicio.skills.image.MlKitTextReader
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class LocalBrainModule {
    @Provides
    @Singleton
    fun provideLocalBrain(): LocalBrain = GeminiNanoBrain()

    @Provides
    @Singleton
    fun provideTextReader(): TextReader = MlKitTextReader()
}
