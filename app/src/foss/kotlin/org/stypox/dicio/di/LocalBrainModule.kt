package org.stypox.dicio.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.stypox.dicio.skills.localbrain.LocalBrain
import org.stypox.dicio.skills.localbrain.NoLocalBrain
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class LocalBrainModule {
    @Provides
    @Singleton
    fun provideLocalBrain(): LocalBrain = NoLocalBrain
}
